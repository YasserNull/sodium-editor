package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trie data structure for efficient word prefix matching.
 * Used for inline word predictions.
 */
public class WordTrie {

    private static final class TrieNode {
        final Map<Character, TrieNode> children = new TreeMap<>();
        @Nullable String word = null;
    }

    private final TrieNode root = new TrieNode();

    public WordTrie() {
    }

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

    @Nullable
    public String findFirstSuggestion(String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;
        TrieNode current = root;
        for (char l : prefix.toCharArray()) {
            TrieNode node = current.children.get(l);
            if (node == null) return null;
            current = node;
        }
        String suggestion = findFirstWordFromNode(current);
        // Don't suggest the exact word the user has already typed.
        if (suggestion != null && suggestion.equals(prefix)) {
            return null;
        }
        return suggestion;
    }

    private String findFirstWordFromNode(TrieNode node) {
        if (node.word != null) return node.word;
        // Using TreeMap makes this loop alphabetically deterministic.
        for (TrieNode childNode : node.children.values()) {
            String found = findFirstWordFromNode(childNode);
            if (found != null) return found;
        }
        return null;
    }
}
