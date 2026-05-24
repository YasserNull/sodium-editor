package com.yn.sodiumeditor.core.autocompletion; 
import com.yn.sodiumeditor.SodiumEditor;
import androidx.annotation.Nullable;
import android.util.Log;
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

    private static final String TAG = "SodiumPathCompletion";
    private static final String ANDROID_PUBLIC_STORAGE_ROOT = "/storage/emulated/0";
    private static final String[] ANDROID_PUBLIC_DIRECTORY_NAMES = {
            "Alarms/",
            "Android/",
            "Audiobooks/",
            "DCIM/",
            "Documents/",
            "Download/",
            "Movies/",
            "Music/",
            "Notifications/",
            "Pictures/",
            "Podcasts/",
            "Ringtones/"
    };

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
        if (fragment.equals(lastPathQuery) && lastPathSuggestion != null) {
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
            List<String> androidPublicFallbackNames =
                    dir == null ? null : getAndroidPublicDirectoryFallbackNames(dir);
            if (androidPublicFallbackNames != null && !androidPublicFallbackNames.isEmpty()) {
                String suggestion = buildSuggestionFromNames(fragment, prefix, androidPublicFallbackNames);
                logPathCompletion("fallback android-root invalid-dir fragment='" + fragment
                        + "' prefix='" + prefix + "' suggestion='" + suggestion + "'");
                lastPathQuery = fragment;
                lastPathSuggestion = suggestion;
                return suggestion;
            }
            logPathCompletion("miss invalid-dir fragment='" + fragment + "' dir="
                    + (dir == null ? "null" : dir.getAbsolutePath())
                    + " exists=" + (dir != null && dir.exists())
                    + " isDirectory=" + (dir != null && dir.isDirectory()));
            lastPathQuery = fragment;
            lastPathSuggestion = null;
            return null;
        }

        File[] entries = dir.listFiles();
        List<String> androidPublicFallbackNames = getAndroidPublicDirectoryFallbackNames(dir);
        if (entries == null || entries.length == 0) {
            if (androidPublicFallbackNames == null || androidPublicFallbackNames.isEmpty()) {
                logPathCompletion("miss empty-dir fragment='" + fragment + "' dir="
                        + dir.getAbsolutePath()
                        + " canRead=" + dir.canRead()
                        + " entriesNull=" + (entries == null));
                lastPathQuery = fragment;
                lastPathSuggestion = null;
                return null;
            }
            String suggestion = buildSuggestionFromNames(fragment, prefix, androidPublicFallbackNames);
            logPathCompletion("fallback android-root fragment='" + fragment + "' prefix='" + prefix
                    + "' suggestion='" + suggestion + "'");
            lastPathQuery = fragment;
            lastPathSuggestion = suggestion;
            return suggestion;
        }

        List<String> matches = new ArrayList<>();
        boolean allowHidden = prefix.startsWith(".");
        for (File entry : entries) {
            String name = entry.getName();
            if (!allowHidden && name.startsWith(".")) continue;
            if (startsWithIgnoreCase(name, prefix)) {
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
            } else if (androidPublicFallbackNames != null && !androidPublicFallbackNames.isEmpty()) {
                suggestion = buildSuggestionFromNames(fragment, prefix, androidPublicFallbackNames);
            }
        }

        logPathCompletion("result fragment='" + fragment + "' dir=" + dir.getAbsolutePath()
                + " prefix='" + prefix + "' entries=" + entries.length
                + " suggestion='" + suggestion + "'");
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
            } else if (name.length() == best.length() && name.compareToIgnoreCase(best) < 0) {
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
            int score = commonPrefixLengthIgnoreCase(prefix, name);
            if (score > bestScore) {
                bestScore = score;
                bestName = entry.isDirectory() ? name + "/" : name;
            } else if (score == bestScore && bestName != null && name.compareToIgnoreCase(bestName) < 0) {
                bestName = entry.isDirectory() ? name + "/" : name;
            }
        }
        return (bestScore <= 0 || bestName == null) ? null : bestName.substring(bestScore);
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

    public int commonPrefixLengthIgnoreCase(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(i))) i++;
        return i;
    }

    public boolean startsWithIgnoreCase(String value, String prefix) {
        if (prefix.length() > value.length()) return false;
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    @Nullable
    public List<String> getAndroidPublicDirectoryFallbackNames(File dir) {
        String path = dir.getAbsolutePath();
        if (!ANDROID_PUBLIC_STORAGE_ROOT.equals(path)) return null;
        List<String> names = new ArrayList<>(ANDROID_PUBLIC_DIRECTORY_NAMES.length);
        Collections.addAll(names, ANDROID_PUBLIC_DIRECTORY_NAMES);
        return names;
    }

    @Nullable
    public String buildSuggestionFromNames(String fragment, String prefix, List<String> names) {
        List<String> matches = new ArrayList<>();
        boolean allowHidden = prefix.startsWith(".");
        for (String name : names) {
            if (!allowHidden && name.startsWith(".")) continue;
            if (startsWithIgnoreCase(name, prefix)) {
                matches.add(name);
            }
        }
        if (!matches.isEmpty()) {
            Collections.sort(matches, String.CASE_INSENSITIVE_ORDER);
            String chosen = chooseClosestPrefix(matches);
            return fragment + chosen.substring(prefix.length());
        }

        int bestScore = -1;
        String bestName = null;
        for (String name : names) {
            if (!allowHidden && name.startsWith(".")) continue;
            int score = commonPrefixLengthIgnoreCase(prefix, name);
            if (score > bestScore) {
                bestScore = score;
                bestName = name;
            } else if (score == bestScore && bestName != null && name.compareToIgnoreCase(bestName) < 0) {
                bestName = name;
            }
        }
        return bestScore <= 0 || bestName == null ? null : fragment + bestName.substring(bestScore);
    }

    /**
     * Check if a character is a valid path character.
     */
    public boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c) || c == '/' || c == '.' || c == '_' || c == '-' || c == '~';
    }

    private void logPathCompletion(String message) {
        if (SodiumEditor.DEBUG_LOGS) Log.d(TAG, message);
    }

    /**
     * Get the current path fragment before the cursor.
     */
    public String getCurrentPathFragment() {
        String line = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
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
        updatePathSuggestionInternal(true);
    }

    /**
     * Updates path suggestion from the shared auto-completion update path.
     *
     * @return true when the current cursor context is a path context and word completion should not run.
     */
    public boolean updatePathSuggestionFromAutoCompletion() {
        return updatePathSuggestionInternal(false);
    }

    private boolean updatePathSuggestionInternal(boolean clearNonPathSuggestion) {
        if (!isAutoPathCompletionEnabled) {
            if (editor.autoCompletion != null && editor.autoCompletion.activeSuggestionIsPath) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return false;
        }

        String line = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
        if (line == null) {
            if (editor.autoCompletion != null && (clearNonPathSuggestion || editor.autoCompletion.activeSuggestionIsPath)) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return false;
        }

        // Do not show suggestions if the cursor is in the middle of a word
        if (editor.cursor.cursorChar < line.length() && Character.isLetterOrDigit(line.charAt(editor.cursor.cursorChar))) {
            if (editor.autoCompletion != null && (clearNonPathSuggestion || editor.autoCompletion.activeSuggestionIsPath)) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return false;
        }

        // Do not show suggestions if there is non-whitespace text after the cursor
        if (editor.cursor.cursorChar < line.length() && !line.substring(editor.cursor.cursorChar).trim().isEmpty()) {
            if (editor.autoCompletion != null && (clearNonPathSuggestion || editor.autoCompletion.activeSuggestionIsPath)) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return false;
        }

        String pathFragment = getCurrentPathFragment();
        if (pathFragment.isEmpty()) {
            if (editor.autoCompletion != null && editor.autoCompletion.activeSuggestionIsPath) {
                editor.autoCompletion.clearActiveSuggestion();
            }
            return false;
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
        return true;
    }

    /**
     * Accept the current path completion suggestion.
     */
    public void acceptPathCompletion() {
        if (editor.autoCompletion == null || editor.autoCompletion.activeSuggestion == null) {
            return;
        }
        if (!editor.autoCompletion.activeSuggestionIsPath) {
            return;
        }
        if (!isAutoPathCompletionEnabled) {
            return;
        }

        editor.ime.commitComposing(false);
        editor.autoCompletion.suggestionAcceptedThisTouch = true;

        String textToInsert = editor.autoCompletion.activeSuggestion;
        editor.autoCompletion.clearActiveSuggestion();
        editor.selection.hasSelection = false;
        editor.selection.isSelectAllActive = false;
        editor.selection.isEntireFileSelected = false;
        editor.editOperators.insertStringAtCursor(textToInsert);

        editor.view.restartInput();
    }
}
