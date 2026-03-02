package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Engine class for path predictions.
 * Handles finding and suggesting file/directory paths.
 */
public class PathPredictionEngine {

    public PathPredictionEngine() {
    }

    @Nullable
    public String findPathSuggestion(String fragment, String[] cache) {
        if (fragment.equals(cache[0])) {
            return cache[1];
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
            cache[0] = fragment;
            cache[1] = null;
            return null;
        }

        File[] entries = dir.listFiles();
        if (entries == null || entries.length == 0) {
            cache[0] = fragment;
            cache[1] = null;
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

        cache[0] = fragment;
        cache[1] = suggestion;
        return suggestion;
    }

    @Nullable
    private File resolveBaseDir(String expanded, String fragment, String dirPart, @Nullable String home) {
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

    @Nullable
    private File getDefaultBaseDir() {
        String home = getHomeDir();
        if (home != null) return new File(home);
        return new File("/");
    }

    @Nullable
    private String getHomeDir() {
        String home = System.getenv("HOME");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("user.home");
        }
        return (home == null || home.isEmpty()) ? null : home;
    }

    private String chooseClosestPrefix(List<String> matches) {
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

    @Nullable
    private String chooseClosestByCommonPrefix(
            File[] entries, String prefix, boolean allowHidden) {
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

    private int commonPrefixLength(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    public boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c)
                || c == '/'
                || c == '.'
                || c == '_'
                || c == '-'
                || c == '~';
    }

    public String getCurrentPathFragment(String line, int cursorChar) {
        if (cursorChar == 0 || cursorChar > line.length()) {
            return "";
        }
        int start = cursorChar;
        while (start > 0 && isPathChar(line.charAt(start - 1))) {
            start--;
        }
        String fragment = line.substring(start, cursorChar);
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
}
