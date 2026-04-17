package com.yn.sodiumeditor.core.autocompletion; 

import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.core.guides.indent.IndentGuides;

/**
 * Auto-completion functionality for SodiumEditor.
 * Handles word-based suggestions using a Trie data structure.
 */
public class AutoCompletion {

    private final SodiumEditor editor;

    // Auto-completion state
    public boolean isAutoCompletionEnabled = true;
    public final Trie suggestionTrie = new Trie();

    // Active suggestion state
    public String activeSuggestion = null;
    public int activeSuggestionLine;
    public int activeSuggestionCharStart;
    public String activeSuggestionWordFragment = "";
    public boolean activeSuggestionIsPath = false;
    public final RectF activeSuggestionRect = new RectF();

    // Suggestion paint
    public final Paint suggestionPaint = new Paint();

    // Suggestion text size
    public boolean isSuggestionTextSizeCustom = false;
    public float suggestionTextSizeScale = 1f;

    // Debounce constants
    public static final long SUGGESTION_UPDATE_DEBOUNCE_MS = 40L;
    public long lastSuggestionUpdateUptime = 0L;
    public boolean suggestionUpdateScheduled = false;
    public final Runnable suggestionUpdateRunnable = () -> {
        suggestionUpdateScheduled = false;
        lastSuggestionUpdateUptime = SystemClock.uptimeMillis();
        updateSuggestionInternal();
    };

    // Touch flag
    public boolean suggestionAcceptedThisTouch = false;

    public AutoCompletion(SodiumEditor editor) {
        this.editor = editor;
        initSuggestionPaint();
    }

    private void initSuggestionPaint() {
        suggestionPaint.set(editor.textRender.paint);
        suggestionPaint.setColor(0xFFAAAAAA);
        suggestionPaint.setAntiAlias(true);
        suggestionPaint.setSubpixelText(true);
        suggestionPaint.setHinting(Paint.HINTING_ON);
    }

    /**
     * Set auto-completion enabled state.
     */
    public void setAutoCompletionEnabled(boolean enabled) {
        this.isAutoCompletionEnabled = enabled;
        if (!enabled && !activeSuggestionIsPath) {
            clearActiveSuggestion();
        }
        editor.invalidate();
    }

    /**
     * Get auto-completion enabled state.
     */
    public boolean isAutoCompletionEnabled() {
        return isAutoCompletionEnabled;
    }

    /**
     * Set suggestions from a list of keywords.
     */
    public void setSuggestions(List<String> keywords, int color) {
        suggestionTrie.clear();
        if (keywords != null) {
            for (String word : keywords) {
                suggestionTrie.insert(word);
            }
        }
        suggestionPaint.setColor(color);
        clearActiveSuggestion();
        editor.invalidate();
    }

    /**
     * Accept the current auto-completion suggestion.
     */
    public void acceptAutoCompletion() {
        Log.d("AutoCompletion", "acceptAutoCompletion: Entered.");
        if (activeSuggestion == null) {
            Log.d("AutoCompletion", "acceptAutoCompletion: Bailed out (no active suggestion).");
            return;
        }
        if (activeSuggestionIsPath) {
            Log.d("AutoCompletion", "acceptAutoCompletion: Bailed out (this is a path suggestion).");
            return;
        }
        if (!isAutoCompletionEnabled) {
            Log.d("AutoCompletion", "acceptAutoCompletion: Bailed out (disabled).");
            return;
        }

        editor.ime.commitComposing(false);
        suggestionAcceptedThisTouch = true;

        String textToInsert = activeSuggestion;
        clearActiveSuggestion();
        editor.selection.hasSelection = false;
        editor.selection.isSelectAllActive = false;
        editor.selection.isEntireFileSelected = false;
        Log.d("AutoCompletion", "acceptAutoCompletion: Inserting text.");
        editor.editOperators.insertStringAtCursor(textToInsert);
        Log.d("AutoCompletion", "acceptAutoCompletion: Text inserted.");

        editor.view.restartInput();
    }

    /**
     * Clear the active suggestion.
     */
    public void clearActiveSuggestion() {
        if (activeSuggestion != null) {
            activeSuggestion = null;
            activeSuggestionRect.setEmpty();
            activeSuggestionIsPath = false;
            editor.invalidate();
        }
    }

    /**
     * Update suggestion based on current cursor position.
     */
    public void updateSuggestion() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            editor.post(this::updateSuggestion);
            return;
        }
        editor.ime.updateImeSelection();
        long now = SystemClock.uptimeMillis();
        if (now - lastSuggestionUpdateUptime < SUGGESTION_UPDATE_DEBOUNCE_MS) {
            if (!suggestionUpdateScheduled) {
                suggestionUpdateScheduled = true;
                editor.caret.mainHandler.postDelayed(suggestionUpdateRunnable, SUGGESTION_UPDATE_DEBOUNCE_MS);
            }
            return;
        }
        lastSuggestionUpdateUptime = now;
        updateSuggestionInternal();
    }

    /**
     * Internal suggestion update logic.
     */
    public void updateSuggestionInternal() {
        String line = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
        if (line == null) {
            clearActiveSuggestion();
            return;
        }

        if (!isAutoCompletionEnabled) {
            clearActiveSuggestion();
            return;
        }

        // Do not show suggestions if the cursor is in the middle of a word
        if (editor.cursor.cursorChar < line.length() && Character.isLetterOrDigit(line.charAt(editor.cursor.cursorChar))) {
            clearActiveSuggestion();
            return;
        }

        // Do not show suggestions if there is non-whitespace text after the cursor
        if (editor.cursor.cursorChar < line.length() && !line.substring(editor.cursor.cursorChar).trim().isEmpty()) {
            clearActiveSuggestion();
            return;
        }

        String wordFragment = getCurrentWordFragment();
        if (wordFragment.isEmpty()) {
            clearActiveSuggestion();
            return;
        }

        // Prevent suggestions inside syntax highlighting
        List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> spans = editor.highliteRender.getHighlightSpansForLine(line, editor.cursor.cursorLine);
        for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan span : spans) {
            if (editor.cursor.cursorChar > span.start && editor.cursor.cursorChar <= span.end) {
                clearActiveSuggestion();
                return;
            }
        }

        String suggestion = suggestionTrie.findFirstSuggestion(wordFragment);
        if (suggestion != null && suggestion.length() > wordFragment.length()) {
            activeSuggestion = suggestion.substring(wordFragment.length());
            activeSuggestionLine = editor.cursor.cursorLine;
            activeSuggestionCharStart = editor.cursor.cursorChar - wordFragment.length();
            activeSuggestionWordFragment = wordFragment;
            activeSuggestionIsPath = false;
        } else {
            clearActiveSuggestion();
        }
        editor.invalidate();
    }

    /**
     * Get the current word fragment before the cursor.
     */
    public String getCurrentWordFragment() {
        String line = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
        if (editor.cursor.cursorChar == 0 || editor.cursor.cursorChar > line.length()) {
            return "";
        }
        int start = editor.cursor.cursorChar;
        while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
            start--;
        }
        return line.substring(start, editor.cursor.cursorChar);
    }

    /**
     * Set suggestion text size.
     */
    public void setSuggestionTextSize(float size) {
        isSuggestionTextSizeCustom = true;
        float px = editor.view.spToPx(size);
        float base = editor.textRender.paint.getTextSize();
        if (base > 0f) {
            suggestionTextSizeScale = px / base;
        } else {
            suggestionTextSizeScale = 1f;
        }
        suggestionPaint.setTextSize(base * suggestionTextSizeScale);
        editor.invalidate();
    }

    /**
     * Trie node for auto-completion.
     */
    public static class TrieNode {
        final java.util.Map<Character, TrieNode> children = new java.util.TreeMap<>();
        String word = null;
    }

    /**
     * Trie data structure for efficient prefix-based suggestions.
     */
    public static class Trie {
        public final TrieNode root = new TrieNode();

        public void clear() {
            root.children.clear();
            root.word = null;
        }

        public void insert(String word) {
            if (word == null || word.isEmpty()) return;
            TrieNode current = root;
            for (char l : word.toCharArray()) {
                current = current.children.computeIfAbsent(l, c -> new TrieNode());
            }
            current.word = word;
        }

        public String findFirstSuggestion(String prefix) {
            if (prefix == null || prefix.isEmpty()) return null;
            TrieNode current = root;
            for (char l : prefix.toCharArray()) {
                TrieNode node = current.children.get(l);
                if (node == null) {
                    return null;
                }
                current = node;
            }
            String suggestion = findFirstWordFromNode(current);
            if (suggestion != null && suggestion.equals(prefix)) {
                return null;
            }
            return suggestion;
        }

        public String findFirstWordFromNode(TrieNode node) {
            if (node == null) return null;
            if (node.word != null) {
                return node.word;
            }
            for (TrieNode childNode : node.children.values()) {
                String found = findFirstWordFromNode(childNode);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
    }
     // Draw auto suggestion
  public void drawAutoSuggestion(Canvas canvas, String lineContent, int globalLine, float textBaselineY) {

    boolean allowSuggestion = activeSuggestionIsPath 
        ? (editor.autoPathCompletion.isAutoPathCompletionEnabled) 
        : (isAutoCompletionEnabled);
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine > lineContent.length()) {
      clearActiveSuggestion();
      return;
    }

    float suggestionStartX_canvas = suggestionPaint.measureText(lineContent, cursorPositionInLine, globalLine);
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);
    float left_view = suggestionStartX_canvas + editor.layout.getTextStartX() - editor.scroll.getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (editor.textRender.isRtl) {
      float baseX = editor.layout.getRtlLineBaseX(lineContent, globalLine);      left_view += baseX;
      right_view += baseX;
    }
    float top_view = globalLine * editor.textRender.lineHeight - editor.scroll.scrollY;
    float bottom_view = (globalLine + 1) * editor.textRender.lineHeight - editor.scroll.scrollY;
    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  public void drawAutoSuggestionWrapped(Canvas canvas, String lineContent, int globalLine, int segStart, int segEnd, int visualIndex, float textBaselineY) {
    
    boolean allowSuggestion = activeSuggestionIsPath
        ? (editor.autoPathCompletion.isAutoPathCompletionEnabled)
        : (isAutoCompletionEnabled);
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine < segStart || cursorPositionInLine > segEnd) return;

    float suggestionStartX_canvas = editor.textRender.measureTextWithVisualSpaces(lineContent, segStart, cursorPositionInLine, editor.textRender.paint);
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);
    float left_view = suggestionStartX_canvas + editor.layout.getTextStartX() - editor.scroll.getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (editor.textRender.isRtl) {
      float baseX = editor.layout.getRtlSegmentBaseX(lineContent, globalLine, segStart, segEnd);      left_view += baseX;
      right_view += baseX;
    }
    float top_view = visualIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
    float bottom_view = top_view + editor.textRender.lineHeight;
    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }
}
