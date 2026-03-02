package com.yn.sodiumeditor.state;

import android.graphics.Rect;

/**
 * State class for editor input and touch handling.
 * Stores touch state, selection state, and IME state.
 */
public class EditorInputState {

    // Touch state
    public boolean pointerDown = false;
    public boolean movedSinceDown = false;
    public float downX = 0f;
    public float downY = 0f;

    // Selection state
    public int lastDoubleTapLine = -1;
    public int lastDoubleTapWordStart = -1;
    public int lastDoubleTapWordEnd = -1;
    public int lastDoubleTapStage = 0;

    // IME state
    public boolean suppressNextCommitText = false;
    public String lastImeCommitText = null;
    public long lastImeCommitUptime = 0L;

    // Display frame
    public final Rect visibleDisplayFrame = new Rect();
    public final int[] tmpLocationInWindow = new int[2];

    // Touch slop (set during initialization)
    public int touchSlop = 0;

    public EditorInputState() {
    }

    public void resetTouchState() {
        pointerDown = false;
        movedSinceDown = false;
        downX = 0f;
        downY = 0f;
    }

    public void setDownPosition(float x, float y) {
        downX = x;
        downY = y;
    }

    public void resetDoubleTapState() {
        lastDoubleTapLine = -1;
        lastDoubleTapWordStart = -1;
        lastDoubleTapWordEnd = -1;
        lastDoubleTapStage = 0;
    }

    public void resetImeState() {
        suppressNextCommitText = false;
        lastImeCommitText = null;
        lastImeCommitUptime = 0L;
    }

    public void setLastImeCommit(String text, long uptime) {
        lastImeCommitText = text;
        lastImeCommitUptime = uptime;
    }

    public boolean hasLastImeCommit() {
        return lastImeCommitText != null;
    }
}
