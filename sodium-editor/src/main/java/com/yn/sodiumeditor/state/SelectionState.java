package com.yn.sodiumeditor.state;

/**
 * State class for selection functionality.
 * Stores selection coordinates and state flags.
 */
public final class SelectionState {
    public boolean hasSelection = false;
    public int selStartLine = 0;
    public int selStartChar = 0;
    public int selEndLine = 0;
    public int selEndChar = 0;
    public boolean selecting = false;
    public boolean isSelectAllActive = false;
    public boolean isEntireFileSelected = false;
    public boolean isLineNumberSelecting = false;
    public int lineNumberSelectAnchorLine = -1;

    public SelectionState() {}

    public boolean hasSelection() {
        return hasSelection;
    }

    public boolean isSelectAllActive() {
        return isSelectAllActive;
    }

    public boolean isEntireFileSelected() {
        return isEntireFileSelected;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public boolean isLineNumberSelecting() {
        return isLineNumberSelecting;
    }

    public int getLineNumberSelectAnchorLine() {
        return lineNumberSelectAnchorLine;
    }

    public void clearSelection() {
        hasSelection = false;
        isSelectAllActive = false;
        isEntireFileSelected = false;
        selecting = false;
        isLineNumberSelecting = false;
        lineNumberSelectAnchorLine = -1;
    }

    public void clearSelectionKeepLineNumberState() {
        hasSelection = false;
        isSelectAllActive = false;
        isEntireFileSelected = false;
        selecting = false;
    }

    public void setSelection(int startLine, int startChar, int endLine, int endChar, boolean selectingNow) {
        selStartLine = startLine;
        selStartChar = startChar;
        selEndLine = endLine;
        selEndChar = endChar;
        hasSelection = !(startLine == endLine && startChar == endChar);
        selecting = selectingNow;
    }

    public void setSelectAllState(boolean selectAll, boolean entireFile) {
        isSelectAllActive = selectAll;
        isEntireFileSelected = entireFile;
    }

    public void setSelecting(boolean selectingNow) {
        selecting = selectingNow;
    }

    public void setLineNumberSelecting(boolean enabled, int anchorLine) {
        isLineNumberSelecting = enabled;
        lineNumberSelectAnchorLine = enabled ? anchorLine : -1;
    }

    public int getSelStartLine() {
        return selStartLine;
    }

    public int getSelStartChar() {
        return selStartChar;
    }

    public int getSelEndLine() {
        return selEndLine;
    }

    public int getSelEndChar() {
        return selEndChar;
    }
}
