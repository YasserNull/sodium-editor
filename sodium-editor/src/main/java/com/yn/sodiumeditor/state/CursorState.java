package com.yn.sodiumeditor.state;

/**
 * State class for cursor functionality.
 * Stores cursor position and IME composing state.
 */
public final class CursorState {
    private int cursorLine = 0;
    private int cursorChar = 0;

    // Composing (IME) state
    private boolean hasComposing = false;
    private int composingLine = 0;
    private int composingOffset = 0;
    private int composingLength = 0;
    private int composingStartLine = -1;
    private int composingStartChar = 0;
    private boolean composingStartActive = false;

    public CursorState() {}

    public int getCursorLine() {
        return cursorLine;
    }

    public void setCursorLine(int cursorLine) {
        this.cursorLine = cursorLine;
    }

    public int getCursorChar() {
        return cursorChar;
    }

    public void setCursorChar(int cursorChar) {
        this.cursorChar = cursorChar;
    }

    public boolean hasComposing() {
        return hasComposing;
    }

    public void setHasComposing(boolean hasComposing) {
        this.hasComposing = hasComposing;
    }

    public int getComposingLine() {
        return composingLine;
    }

    public void setComposingLine(int composingLine) {
        this.composingLine = composingLine;
    }

    public int getComposingOffset() {
        return composingOffset;
    }

    public void setComposingOffset(int composingOffset) {
        this.composingOffset = composingOffset;
    }

    public int getComposingLength() {
        return composingLength;
    }

    public void setComposingLength(int composingLength) {
        this.composingLength = composingLength;
    }

    public int getComposingStartLine() {
        return composingStartLine;
    }

    public void setComposingStartLine(int composingStartLine) {
        this.composingStartLine = composingStartLine;
    }

    public int getComposingStartChar() {
        return composingStartChar;
    }

    public void setComposingStartChar(int composingStartChar) {
        this.composingStartChar = composingStartChar;
    }

    public boolean isComposingStartActive() {
        return composingStartActive;
    }

    public void setComposingStartActive(boolean composingStartActive) {
        this.composingStartActive = composingStartActive;
    }

    public void setCursorPosition(int line, int ch) {
        this.cursorLine = Math.max(0, line);
        this.cursorChar = Math.max(0, ch);
    }

    public void moveCharDelta(int delta) {
        this.cursorChar = Math.max(0, this.cursorChar + delta);
    }

    public void clampCharToLineLength(int line, int lineLength) {
        this.cursorChar = Math.min(this.cursorChar, lineLength);
    }
}
