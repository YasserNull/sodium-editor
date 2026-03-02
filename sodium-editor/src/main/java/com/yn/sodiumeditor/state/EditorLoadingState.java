package com.yn.sodiumeditor.state;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State class for editor loading and large edit operations.
 * Stores loading state and busy indicator state.
 */
public class EditorLoadingState {

    // Loading circle state
    public boolean showLoadingOnFileOpen = true;
    public boolean isInitialFileOpenLoading = false;
    public int initialFileOpenToken = 0;
    public Runnable initialFileOpenShowSpinner = null;
    public final List<Runnable> initialLoadCallbacks = new ArrayList<>();

    // Large edit UI state
    public static final int LARGE_EDIT_LINES = 8000;
    public static final int HIDE_COPY_CUT_LINES = 20000;
    public static final int COPY_CUT_MAX_LINES = 20000;
    public static final int COPY_CUT_MAX_CHARS = 8_000_000;
    public static final int REPLACE_ALL_MAX_COUNT = 100000;
    
    // Mutable config values
    public long copyCutMaxLines = COPY_CUT_MAX_LINES;
    public int copyCutMaxChars = COPY_CUT_MAX_CHARS;
    public int hideCopyCutMaxLines = HIDE_COPY_CUT_LINES;
    public int replaceAllMaxCount = REPLACE_ALL_MAX_COUNT;
    
    public final AtomicInteger largeEditUiToken = new AtomicInteger(0);

    // Max width recalc token
    public int maxWidthRecalcToken = 0;

    public EditorLoadingState() {
    }

    public boolean isShowLoadingOnFileOpen() {
        return showLoadingOnFileOpen;
    }

    public void setShowLoadingOnFileOpen(boolean show) {
        this.showLoadingOnFileOpen = show;
    }

    public boolean isInitialFileOpenLoading() {
        return isInitialFileOpenLoading;
    }

    public void setInitialFileOpenLoading(boolean loading) {
        isInitialFileOpenLoading = loading;
    }

    public int getInitialFileOpenToken() {
        return initialFileOpenToken;
    }

    public void setInitialFileOpenToken(int token) {
        this.initialFileOpenToken = token;
    }

    public Runnable getInitialFileOpenShowSpinner() {
        return initialFileOpenShowSpinner;
    }

    public void setInitialFileOpenShowSpinner(Runnable runnable) {
        this.initialFileOpenShowSpinner = runnable;
    }

    public List<Runnable> getInitialLoadCallbacks() {
        return initialLoadCallbacks;
    }

    public void addInitialLoadCallback(Runnable callback) {
        initialLoadCallbacks.add(callback);
    }

    public void clearInitialLoadCallbacks() {
        initialLoadCallbacks.clear();
    }

    public int getLargeEditLines() {
        return LARGE_EDIT_LINES;
    }

    public int getHideCopyCutLines() {
        return hideCopyCutMaxLines;
    }

    public int getCopyCutMaxLines() {
        return (int) copyCutMaxLines;
    }

    public long getCopyCutMaxLinesLong() {
        return copyCutMaxLines;
    }

    public int getCopyCutMaxChars() {
        return copyCutMaxChars;
    }

    public int getReplaceAllMaxCount() {
        return replaceAllMaxCount;
    }

    public int getLargeEditUiToken() {
        return largeEditUiToken.get();
    }

    public int incrementLargeEditUiToken() {
        return largeEditUiToken.incrementAndGet();
    }

    public int getMaxWidthRecalcToken() {
        return maxWidthRecalcToken;
    }

    public void setMaxWidthRecalcToken(int token) {
        this.maxWidthRecalcToken = token;
    }

    public void resetLoadingState() {
        isInitialFileOpenLoading = false;
        initialFileOpenToken = 0;
        initialFileOpenShowSpinner = null;
        clearInitialLoadCallbacks();
        maxWidthRecalcToken = 0;
    }
}
