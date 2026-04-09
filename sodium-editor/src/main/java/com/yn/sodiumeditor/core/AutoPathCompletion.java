package com.yn.sodiumeditor.core; 

import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.renderer.TextRender;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Auto path completion functionality for SodiumEditor.
 * Handles file system path suggestions.
 */
public class AutoPathCompletion {

    private final SodiumEditor editor;

    // Auto path completion state
    public boolean isAutoPathCompletionEnabled = true;

    // Path suggestion cache
    public String lastPathQuery = null;
    public String lastPathSuggestion = null;

    public AutoPathCompletion(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Set auto path completion enabled state.
     */
    public void setAutoPathCompletionEnabled(boolean enabled) {
        this.isAutoPathCompletionEnabled = enabled;
        if (!enabled && editor.autoCompletion != null && editor.autoCompletion.activeSuggestionIsPath) {
            editor.autoCompletion.clearActiveSuggestion();
        }
        editor.invalidate();
    }

    /**
     * Get auto path completion enabled state.
     */
    public boolean isAutoPathCompletionEnabled() {
        return isAutoPathCompletionEnabled;
    }

    /**
     * Find path suggestion for the given fragment.
     */
    @Nullable
    public String findPathSuggestion(String fragment) {
        if (fragment.equals(lastPathQuery)) {
            return lastPathSuggestion;
        }

        String expanded = fragment;
        String home = getHomeDir();
        if (fragment.startsWith("~") && home != null) {
            if (fragment.equals("~")) {
                expanded = home;
            } else if (fragment.startsWith("~/")) {
                expanded = home + fragment.substring(1);
            }
        }

        int lastSlash = expanded.lastIndexOf('/');
        String dirPart = lastSlash >= 0 ? expanded.substring(0, lastSlash) : "";
        String prefix = lastSlash >= 0 ? expanded.substring(lastSlash + 1) : expanded;
        File dir = resolveBaseDir(expanded, fragment, dirPart, home);
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            lastPathQuery = fragment;
            lastPathSuggestion = null;
            return null;
        }

        File[] entries = dir.listFiles();
        if (entries == null || entries.length == 0) {
            lastPathQuery = fragment;
            lastPathSuggestion = null;
            return null;
        }

        List<String> matches = new ArrayList<>();
        boolean allowHidden = prefix.startsWith(".");
        for (File entry : entries) {
            String name = entry.getName();
            if (!allowHidden && name.startsWith(".")) continue;
            if (name.startsWith(prefix)) {
                matches.add(entry.isDirectory() ? name + "/" : name);
            }
        }
        String suggestion = null;
        if (!matches.isEmpty()) {
            Collections.sort(matches);
            String chosen = chooseClosestPrefix(matches);
            suggestion = fragment + chosen.substring(prefix.length());
        } else {
            String fallback = chooseClosestByCommonPrefix(entries, prefix, allowHidden);
            if (fallback != null) {
                suggestion = fragment + fallback;
            }
        }

        lastPathQuery = fragment;
        lastPathSuggestion = suggestion;
        return suggestion;
    }

    /**
     * Resolve the base directory for path completion.
     */
    @Nullable
    public File resolveBaseDir(String expanded, String fragment, String dirPart, @Nullable String home) {
        if (expanded.startsWith("/")) {
            return new File(dirPart.isEmpty() ? "/" : dirPart);
        }
        if (fragment.startsWith("~") && home != null) {
            return new File(dirPart.isEmpty() ? home : dirPart);
        }
        File base = getDefaultBaseDir();
        if (base == null) return null;
        return dirPart.isEmpty() ? base : new File(base, dirPart);
    }

    /**
     * Get the default base directory for path completion.
     */
    @Nullable
    public File getDefaultBaseDir() {
        if (editor.fileIO.sourceFile != null) {
            File parent = editor.fileIO.sourceFile.getParentFile();
            if (parent != null) return parent;
        }
        String home = getHomeDir();
        if (home != null) return new File(home);
        return new File("/");
    }

    /**
     * Get the home directory path.
     */
    @Nullable
    public String getHomeDir() {
        String home = System.getenv("HOME");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("user.home");
        }
        return (home == null || home.isEmpty()) ? null : home;
    }

    /**
     * Choose the closest prefix from a list of matches.
     */
    public String chooseClosestPrefix(List<String> matches) {
        String best = matches.get(0);
        for (String name : matches) {
            if (name.length() < best.length()) {
                best = name;
            } else if (name.length() == best.length() && name.compareTo(best) < 0) {
                best = name;
            }
        }
        return best;
    }

    /**
     * Choose the closest match by common prefix length.
     */
    @Nullable
    public String chooseClosestByCommonPrefix(File[] entries, String prefix, boolean allowHidden) {
        int bestScore = -1;
        String bestName = null;
        for (File entry : entries) {
            String name = entry.getName();
            if (!allowHidden && name.startsWith(".")) continue;
            int score = commonPrefixLength(prefix, name);
            if (score > bestScore) {
                bestScore = score;
                bestName = entry.isDirectory() ? name + "/" : name;
            } else if (score == bestScore && bestName != null && name.compareTo(bestName) < 0) {
                bestName = entry.isDirectory() ? name + "/" : name;
            }
        }
        return (bestScore <= 0) ? null : bestName;
    }

    /**
     * Get the length of the common prefix between two strings.
     */
    public int commonPrefixLength(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    /**
     * Check if a character is a valid path character.
     */
    public boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c) || c == '/' || c == '.' || c == '_' || c == '-' || c == '~';
    }

    /**
     * Get the current path fragment before the cursor.
     */
    public String getCurrentPathFragment() {
        String line = editor.getLineTextForRender(editor.cursor.cursorLine);
        if (editor.cursor.cursorChar == 0 || editor.cursor.cursorChar > line.length()) {
            return "";
        }
        int start = editor.cursor.cursorChar;
        while (start > 0 && isPathChar(line.charAt(start - 1))) {
            start--;
        }
        String fragment = line.substring(start, editor.cursor.cursorChar);
        if (fragment.isEmpty()) return "";
        if (fragment.startsWith("/")
                || fragment.startsWith("~")
                || fragment.startsWith("./")
                || fragment.startsWith("../")
                || fragment.contains("/")) {
            return fragment;
        }
        return "";
    }

    /**
     * Update path suggestion based on current cursor position.
     */
    public void updatePathSuggestion() {
        if (!isAutoPathCompletionEnabled) {
            if (editor.autoCompletion != null && editor.autoCompletion.activeSuggestionIsPath) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return;
        }

        String line = editor.getLineTextForRender(editor.cursor.cursorLine);
        if (line == null) {
            if (editor.autoCompletion != null) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return;
        }

        // Do not show suggestions if the cursor is in the middle of a word
        if (editor.cursor.cursorChar < line.length() && Character.isLetterOrDigit(line.charAt(editor.cursor.cursorChar))) {
            if (editor.autoCompletion != null) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return;
        }

        // Do not show suggestions if there is non-whitespace text after the cursor
        if (editor.cursor.cursorChar < line.length() && !line.substring(editor.cursor.cursorChar).trim().isEmpty()) {
            if (editor.autoCompletion != null) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return;
        }

        String pathFragment = getCurrentPathFragment();
        if (pathFragment.isEmpty()) {
            if (editor.autoCompletion != null && editor.autoCompletion.activeSuggestionIsPath) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return;
        }

        // Prevent suggestions inside syntax highlighting
        List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> spans = editor.highlite.highlightCache.get(editor.cursor.cursorLine);
        if (spans == null) {
            spans = editor.highlite.calculateSpansForLine(line, editor.cursor.cursorLine);
            editor.highlite.highlightCache.put(editor.cursor.cursorLine, spans);
        }
        for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan span : spans) {
            if (editor.cursor.cursorChar > span.start && editor.cursor.cursorChar <= span.end) {
                if (editor.autoCompletion != null) {
                    editor.autoCompletion.clearActiveSuggestion();
                }
                return;
            }
        }

        String suggestion = findPathSuggestion(pathFragment);
        if (editor.autoCompletion != null) {
            if (suggestion != null && suggestion.length() > pathFragment.length()) {
                editor.autoCompletion.activeSuggestion = suggestion.substring(pathFragment.length());
                editor.autoCompletion.activeSuggestionLine = editor.cursor.cursorLine;
                editor.autoCompletion.activeSuggestionCharStart = editor.cursor.cursorChar - pathFragment.length();
                editor.autoCompletion.activeSuggestionWordFragment = pathFragment;
                editor.autoCompletion.activeSuggestionIsPath = true;
            } else {
                editor.autoCompletion.clearActiveSuggestion();
            }
            editor.invalidate();
        }
    }

    /**
     * Accept the current path completion suggestion.
     */
    public void acceptPathCompletion() {
        Log.d("AutoPathCompletion", "acceptPathCompletion: Entered.");
        if (editor.autoCompletion == null || editor.autoCompletion.activeSuggestion == null) {
            Log.d("AutoPathCompletion", "acceptPathCompletion: Bailed out (no active suggestion).");
            return;
        }
        if (!editor.autoCompletion.activeSuggestionIsPath) {
            Log.d("AutoPathCompletion", "acceptPathCompletion: Bailed out (not a path suggestion).");
            return;
        }
        if (!isAutoPathCompletionEnabled) {
            Log.d("AutoPathCompletion", "acceptPathCompletion: Bailed out (disabled).");
            return;
        }

        editor.ime.commitComposing(false);
        editor.autoCompletion.suggestionAcceptedThisTouch = true;

        String textToInsert = editor.autoCompletion.activeSuggestion;
        editor.autoCompletion.clearActiveSuggestion();
        editor.selection.hasSelection = false;
        editor.selection.isSelectAllActive = false;
        editor.selection.isEntireFileSelected = false;
        Log.d("AutoPathCompletion", "acceptPathCompletion: Inserting text.");
        editor.editOperators.insertStringAtCursor(textToInsert);
        Log.d("AutoPathCompletion", "acceptPathCompletion: Text inserted.");

        editor.restartInput();
    }
}
