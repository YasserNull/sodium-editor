package com.yn.sodiumeditor.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * State class for editor line index.
 * Stores index data and caching information.
 */
public class EditorIndexState {

    // Index state
    public final Object lineOffsetsLock = new Object();
    public long[] lineOffsets = new long[0];
    public volatile boolean isIndexReady = false;
    public volatile boolean isIndexBuilding = false;
    public volatile boolean isIndexDisabled = false;
    public String indexDisabledPath = null;
    public long indexDisabledFileLength = -1L;

    // Direct line cache
    public final LinkedHashMap<Integer, String> directLineCache =
            new LinkedHashMap<Integer, String>(600, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                    return size() > 600;
                }
            };

    // Go to line version
    private final Object goToLineVersionLock = new Object();
    private int goToLineVersion = 0;
    
    // Public goToLine version getter/setter
    public int getGoToLineVersion() {
        synchronized (goToLineVersionLock) {
            return goToLineVersion;
        }
    }
    
    public void setGoToLineVersion(int version) {
        synchronized (goToLineVersionLock) {
            goToLineVersion = version;
        }
    }
    
    public int incrementGoToLineVersion() {
        synchronized (goToLineVersionLock) {
            return ++goToLineVersion;
        }
    }

    public EditorIndexState() {
    }

    public void setLineOffsets(long[] offsets) {
        lineOffsets = offsets;
    }

    public long getLineOffset(int line) {
        if (line < 0 || line >= lineOffsets.length) return -1;
        return lineOffsets[line];
    }

    public int getLineCount() {
        return lineOffsets.length;
    }

    public void setIndexReady(boolean ready) {
        isIndexReady = ready;
    }

    public void setIndexBuilding(boolean building) {
        isIndexBuilding = building;
    }

    public void setIndexDisabled(boolean disabled, String path, long fileLength) {
        isIndexDisabled = disabled;
        indexDisabledPath = path;
        indexDisabledFileLength = fileLength;
    }

    public void clearIndex() {
        lineOffsets = new long[0];
        isIndexReady = false;
        isIndexBuilding = false;
    }

    public void clearDirectLineCache() {
        directLineCache.clear();
    }

    public String getCachedDirectLine(int line) {
        return directLineCache.get(line);
    }

    public void putCachedDirectLine(int line, String text) {
        directLineCache.put(line, text);
    }

    public void removeCachedDirectLine(int line) {
        directLineCache.remove(line);
    }

    public boolean isIndexBuilding() {
        return isIndexBuilding;
    }

    public boolean isIndexDisabled() {
        return isIndexDisabled;
    }
}
