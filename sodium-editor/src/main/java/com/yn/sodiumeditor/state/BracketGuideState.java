package com.yn.sodiumeditor.state;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/**
 * State class for bracket guide functionality.
 * Stores bracket guide state including cache fields and token storage.
 */
public class BracketGuideState {

    private boolean bracketGuidesEnabled = false;

    // Cache fields
    private int bracketGuideCacheStartLine = -1;
    private int bracketGuideCacheEndLine = -1;
    private int bracketGuideCacheEditVersion = -1;
    private int bracketGuideCacheConfigHash = 0;

    // Token storage
    public final ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow = new ArrayList<>();

    // Buffer for tracking seen x positions
    public float[] guideSeenXBuffer;
    public int guideSeenXCount = 0;

    // Inner state class for parsing
    public static class BracketGuideStateInner {
        public boolean inBlockComment;
        public int stringState;
        public final ArrayDeque<BracketGuideToken> stack = new ArrayDeque<>();

        public BracketGuideStateInner(boolean inBlockComment, int stringState) {
            this.inBlockComment = inBlockComment;
            this.stringState = stringState;
        }
    }

    public BracketGuideState() {
    }

    public boolean isBracketGuidesEnabled() {
        return bracketGuidesEnabled;
    }

    public void setBracketGuidesEnabled(boolean enabled) {
        this.bracketGuidesEnabled = enabled;
    }

    public int getCacheStartLine() {
        return bracketGuideCacheStartLine;
    }

    public void setCacheStartLine(int startLine) {
        this.bracketGuideCacheStartLine = startLine;
    }

    public int getCacheEndLine() {
        return bracketGuideCacheEndLine;
    }

    public void setCacheEndLine(int endLine) {
        this.bracketGuideCacheEndLine = endLine;
    }

    public int getCacheEditVersion() {
        return bracketGuideCacheEditVersion;
    }

    public void setCacheEditVersion(int version) {
        this.bracketGuideCacheEditVersion = version;
    }

    public int getCacheConfigHash() {
        return bracketGuideCacheConfigHash;
    }

    public void setCacheConfigHash(int hash) {
        this.bracketGuideCacheConfigHash = hash;
    }

    public void invalidateCache() {
        bracketGuideCacheStartLine = -1;
        bracketGuideCacheEndLine = -1;
        bracketGuideCacheEditVersion = -1;
        bracketGuideCacheConfigHash = 0;
        bracketGuideTokensWindow.clear();
    }

    public boolean isCacheValid(int start, int end, int version, int configHash) {
        return start == bracketGuideCacheStartLine
                && end == bracketGuideCacheEndLine
                && version == bracketGuideCacheEditVersion
                && configHash == bracketGuideCacheConfigHash;
    }

    public List<BracketGuideToken> getTokensForLine(int globalLine, int start, int end) {
        if (start < 0 || globalLine < start || globalLine > end) return null;
        int idx = globalLine - start;
        if (idx < 0 || idx >= bracketGuideTokensWindow.size()) return null;
        return bracketGuideTokensWindow.get(idx);
    }

    public void clearTokensWindow() {
        bracketGuideTokensWindow.clear();
    }

    public void ensureTokensWindowCapacity(int capacity) {
        bracketGuideTokensWindow.ensureCapacity(capacity);
    }

    public void addTokensToWindow(List<BracketGuideToken> tokens) {
        bracketGuideTokensWindow.add(tokens);
    }

    public int getTokensWindowSize() {
        return bracketGuideTokensWindow.size();
    }

    public void resetGuideSeenX() {
        guideSeenXCount = 0;
    }

    public void incrementGuideSeenX() {
        guideSeenXCount++;
    }

    public int getGuideSeenXCount() {
        return guideSeenXCount;
    }

    public boolean hasGuideSeenXBuffer() {
        return guideSeenXBuffer != null;
    }

    public float getGuideSeenXAt(int index) {
        if (guideSeenXBuffer == null || index >= guideSeenXCount) return 0f;
        return guideSeenXBuffer[index];
    }

    public void ensureGuideSeenXBuffer(int minSize) {
        if (guideSeenXBuffer == null || guideSeenXBuffer.length < minSize) {
            float[] next = new float[Math.max(16, minSize)];
            if (guideSeenXBuffer != null && guideSeenXCount > 0) {
                System.arraycopy(guideSeenXBuffer, 0, next, 0, guideSeenXCount);
            }
            guideSeenXBuffer = next;
        }
    }

    public void addGuideSeenX(float x) {
        if (guideSeenXCount < guideSeenXBuffer.length) {
            guideSeenXBuffer[guideSeenXCount++] = x;
        }
    }
}
