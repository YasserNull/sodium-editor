package com.yn.sodiumeditor.core;

import android.graphics.Rect;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State manager for SodiumEditor.
 * Manages editor state including window, cache, and line data.
 */
public class EditorStateManager {

    // Window state
    public final List<String> linesWindow = new ArrayList<>();
    public int windowStartLine = 0;
    public int windowSize = 30;
    public int prefetchLines = 10;

    // Line caches
    public final LinkedHashMap<Integer, String> modifiedLines = new LinkedHashMap<>();
    public final LinkedHashMap<Integer, Float> lineWidthCache;
    public int lineWidthCacheSize = 200;
    public float currentMaxWindowLineWidth = 0f;
    public float globalMaxLineWidth = 0f;

    // Character width cache
    public int prefetchCols = 512;
    public int colsWidthCacheSize = 256;
    public final LinkedHashMap<Integer, Float> avgCharWidthCache =
            new LinkedHashMap<Integer, Float>(colsWidthCacheSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
                    return size() > colsWidthCacheSize;
                }
            };

    // Streamed line info
    public final SparseIntArray streamedLineLengths = new SparseIntArray();
    public final SparseIntArray streamedLineSliceStarts = new SparseIntArray();
    public boolean streamedSliceUpdatePending = false;
    public int streamedSliceUpdateToken = 0;
    public final int[] streamedSliceTmp = new int[2];
    public final Object streamedLinesLock = new Object();

    // Window state
    public volatile boolean isWindowLoading = false;
    public volatile boolean isEof = false;
    public int drawBaseLine = 0;

    // Display state
    public final Rect visibleDisplayFrame = new Rect();
    public int keyboardHeight = 0;

    // Visible char range temp buffer
    public final int[] visibleCharRangeTmp = new int[2];
    public final int[] visibleCharRangeTmpForRender = new int[2];

    // Edit version tracking
    private final AtomicInteger editVersion = new AtomicInteger(0);

    public EditorStateManager() {
        lineWidthCache = new LinkedHashMap<Integer, Float>(lineWidthCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
                return size() > lineWidthCacheSize;
            }
        };
    }

    public int getEditVersion() {
        return editVersion.get();
    }

    public void incrementEditVersion() {
        editVersion.incrementAndGet();
    }

    public void clearWindow() {
        linesWindow.clear();
        windowStartLine = 0;
        isWindowLoading = false;
        isEof = false;
    }

    public void setWindowData(List<String> lines, int startLine) {
        linesWindow.clear();
        linesWindow.addAll(lines);
        windowStartLine = startLine;
    }

    public int getWindowSize() {
        return linesWindow.size();
    }

    public String getLineFromWindow(int index) {
        if (index < 0 || index >= linesWindow.size()) return null;
        return linesWindow.get(index);
    }

    public boolean isLineInWindow(int line) {
        return line >= windowStartLine && line < windowStartLine + linesWindow.size();
    }

    public void clearCaches() {
        modifiedLines.clear();
        lineWidthCache.clear();
        avgCharWidthCache.clear();
        streamedLineLengths.clear();
        streamedLineSliceStarts.clear();
        currentMaxWindowLineWidth = 0f;
        globalMaxLineWidth = 0f;
    }

    public void clearLineCache(int line) {
        modifiedLines.remove(line);
        lineWidthCache.remove(line);
        avgCharWidthCache.remove(line);
    }

    public Float getCachedLineWidth(int line) {
        return lineWidthCache.get(line);
    }

    public void putCachedLineWidth(int line, float width) {
        lineWidthCache.put(line, width);
    }

    public Float getCachedCharWidth(int line) {
        return avgCharWidthCache.get(line);
    }

    public void putCachedCharWidth(int line, float width) {
        avgCharWidthCache.put(line, width);
    }

    public String getModifiedLine(int line) {
        return modifiedLines.get(line);
    }

    public void putModifiedLine(int line, String text) {
        modifiedLines.put(line, text);
    }

    public void removeModifiedLine(int line) {
        modifiedLines.remove(line);
    }

    public boolean hasModifiedLine(int line) {
        return modifiedLines.containsKey(line);
    }

    public int getStreamedLineLength(int line) {
        return streamedLineLengths.get(line, -1);
    }

    public void putStreamedLineLength(int line, int length) {
        streamedLineLengths.put(line, length);
    }

    public int getStreamedLineSliceStart(int line) {
        return streamedLineSliceStarts.get(line, 0);
    }

    public void putStreamedLineSliceStart(int line, int start) {
        streamedLineSliceStarts.put(line, start);
    }

    public void setKeyboardHeight(int height) {
        keyboardHeight = height;
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }
}
